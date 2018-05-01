package mfi.photos.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;

public class AES {

	public static final String FILE_SUFFIX = ".cipher";

	// AES specification - changing will break existing encrypted streams!
	private static final String CIPHER_SPEC = "AES/CBC/PKCS5Padding";

	// Key derivation specification - changing will break existing streams!
	private static final String KEYGEN_SPEC = "PBKDF2WithHmacSHA1";
	private static final int FIX_IV_SIZE = 16;
	private static final int SALT_LENGTH = 16; // in bytes
	private static final int AUTH_KEY_LENGTH = 8; // in bytes
	private static final int AES_KEY_LENGTH = 256;
	private static final int ITERATIONS = 512;

	private static final int LENGTH_PREFIX = 20;

	// private static int sumPayload;

	// Process input/output streams in chunks - arbitrary
	private static final int BUFFER_SIZE_DEC = 1024 * 512; // 512k
	private static final int BUFFER_SIZE_ENC = BUFFER_SIZE_DEC - 16;

	/**
	 * @return a new pseudorandom salt of the specified length
	 */
	private static byte[] generateSalt(int length) {
		Random r = new SecureRandom();
		byte[] salt = new byte[length];
		r.nextBytes(salt);
		return salt;
	}

	/**
	 * Derive an AES encryption key and authentication key from given password
	 * and salt, using PBKDF2 key stretching. The authentication key is 64 bits
	 * long.
	 * 
	 * @param keyLength
	 *            length of the AES key in bits (128, 192, or 256)
	 * @param password
	 *            the password from which to derive the keys
	 * @param salt
	 *            the salt from which to derive the keys
	 * @return a Keys object containing the two generated keys
	 */
	private static Keys keygen(int keyLength, char[] password, byte[] salt) {
		SecretKeyFactory factory;
		try {
			factory = SecretKeyFactory.getInstance(KEYGEN_SPEC);
		} catch (NoSuchAlgorithmException impossible) {
			return null;
		}
		// derive a longer key, then split into AES key and authentication key
		KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, keyLength + AUTH_KEY_LENGTH * 8);
		SecretKey tmp = null;
		try {
			tmp = factory.generateSecret(spec);
		} catch (InvalidKeySpecException impossible) {
		}
		byte[] fullKey = tmp.getEncoded();
		SecretKey authKey = new SecretKeySpec( // key for password
												// authentication
				Arrays.copyOfRange(fullKey, 0, AUTH_KEY_LENGTH), "AES");
		SecretKey encKey = new SecretKeySpec( // key for AES encryption
				Arrays.copyOfRange(fullKey, AUTH_KEY_LENGTH, fullKey.length), "AES");
		return new Keys(encKey, authKey);
	}

	/**
	 * Encrypts a stream of data. The encrypted stream consists of a header
	 * followed by the raw AES data. The header is broken down as follows:<br/>
	 * <ul>
	 * <li><b>keyLength</b>: AES key length in bytes (valid for 16, 24, 32) (1
	 * byte)</li>
	 * <li><b>salt</b>: pseudorandom salt used to derive keys from password (16
	 * bytes)</li>
	 * <li><b>authentication key</b> (derived from password and salt, used to
	 * check validity of password upon decryption) (8 bytes)</li>
	 * <li><b>IV</b>: pseudorandom AES initialization vector (16 bytes)</li>
	 * </ul>
	 */
	public static long encrypt(long length, char[] password, InputStream input, String filename, String dir,
			Consumer<ChunkData> chunkConsumer) {

		long bytesWritten = 0;

		ByteBuffer byteBuffer = ByteBuffer.allocate(LENGTH_PREFIX + SALT_LENGTH + AUTH_KEY_LENGTH + FIX_IV_SIZE);

		String sizeString = StringUtils.leftPad(String.valueOf(length), LENGTH_PREFIX, '0');
		byteBuffer.put(sizeString.getBytes(StandardCharsets.UTF_8));

		// generate salt and derive keys for authentication and encryption
		byte[] salt = generateSalt(SALT_LENGTH);
		Keys keys = keygen(AES_KEY_LENGTH, password, salt);
		Cipher encrypt = null;

		// initialize AES encryption
		try {
			encrypt = Cipher.getInstance(CIPHER_SPEC);
			encrypt.init(Cipher.ENCRYPT_MODE, keys.encryption);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException impossible) {
		} catch (InvalidKeyException e) { // 192 or 256-bit AES not
			throw new RuntimeException("AES key length not available:" + AES_KEY_LENGTH, e);
		}

		// get initialization vector
		byte[] iv = null;
		try {
			iv = encrypt.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
		} catch (InvalidParameterSpecException impossible) {
		}

		// write authentication and AES initialization data
		byteBuffer.put(salt);
		byteBuffer.put(keys.authentication.getEncoded());
		byteBuffer.put(iv);

		ChunkData headChunk = new ChunkData(filename, dir, byteBuffer.capacity(), byteBuffer.array(), false);
		chunkConsumer.accept(headChunk);
		bytesWritten += byteBuffer.capacity();

		// read data from input into buffer, encrypt and write to output
		byte[] buffer = new byte[BUFFER_SIZE_ENC];
		int numRead;
		byte[] encrypted = null;
		try {
			while ((numRead = input.read(buffer)) > 0) {

				try {
					encrypted = encrypt.doFinal(buffer, 0, numRead);
					if (encrypted != null) {
						ChunkData payloadChunk = new ChunkData(filename, dir, encrypted.length, encrypted, true);
						chunkConsumer.accept(payloadChunk);
						bytesWritten += encrypted.length;
					}
				} catch (IllegalBlockSizeException | BadPaddingException e) {
					throw new RuntimeException("error in doFinal", e);
				}
			}
		} catch (IOException ioe) {
			throw new RuntimeException("error reading input stream", ioe);
		}

		return bytesWritten;
	}

	public static void encrypt(long length, char[] password, InputStream input, OutputStream output) {

		// sumPayload = 0;

		String sizeString = StringUtils.leftPad(String.valueOf(length), LENGTH_PREFIX, '0');
		write(output, sizeString.getBytes(StandardCharsets.UTF_8), "size");

		// generate salt and derive keys for authentication and encryption
		byte[] salt = generateSalt(SALT_LENGTH);
		Keys keys = keygen(AES_KEY_LENGTH, password, salt);
		Cipher encrypt = null;

		// initialize AES encryption
		try {
			encrypt = Cipher.getInstance(CIPHER_SPEC);
			encrypt.init(Cipher.ENCRYPT_MODE, keys.encryption);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException impossible) {
		} catch (InvalidKeyException e) { // 192 or 256-bit AES not
			throw new RuntimeException("AES key length not available:" + AES_KEY_LENGTH, e);
		}

		// get initialization vector
		byte[] iv = null;
		try {
			iv = encrypt.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
		} catch (InvalidParameterSpecException impossible) {
		}

		// write authentication and AES initialization data
		write(output, salt, "salt");
		write(output, keys.authentication.getEncoded(), "authkey");
		write(output, iv, "iv");

		// read data from input into buffer, encrypt and write to output
		byte[] buffer = new byte[BUFFER_SIZE_ENC];
		int numRead;
		byte[] encrypted = null;
		try {
			while ((numRead = input.read(buffer)) > 0) {

				try {
					encrypted = encrypt.doFinal(buffer, 0, numRead);
					if (encrypted != null) {
						write(output, encrypted, "payload");
					}
				} catch (IllegalBlockSizeException | BadPaddingException e) {
					throw new RuntimeException("error in doFinal", e);
				}
			}
		} catch (IOException ioe) {
			throw new RuntimeException("error reading input stream", ioe);
		}
	}

	/**
	 * Decrypts a stream of data that was encrypted by {@link #encrypt}.
	 */
	public static void decrypt(char[] password, InputStream input, OutputStream output, long start,
			long lengthToWrite) {

		// sumPayload = 0;
		long bytesWritten = 0;

		byte[] size = new byte[LENGTH_PREFIX];
		read(input, size, "size");

		// read salt, generate keys, and authenticate password
		byte[] salt = new byte[SALT_LENGTH];
		read(input, salt, "salt");
		Keys keys = keygen(AES_KEY_LENGTH, password, salt);
		byte[] authRead = new byte[AUTH_KEY_LENGTH];
		read(input, authRead, "authkey");
		if (!Arrays.equals(keys.authentication.getEncoded(), authRead)) {
			throw new RuntimeException("invalid password");
		}

		// initialize AES decryption
		byte[] iv = new byte[FIX_IV_SIZE]; // 16-byte I.V. regardless of key
		read(input, iv, "iv");

		Cipher decrypt = null;
		try {
			decrypt = Cipher.getInstance(CIPHER_SPEC);
			decrypt.init(Cipher.DECRYPT_MODE, keys.encryption, new IvParameterSpec(iv));
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException impossible) {
		} catch (InvalidKeyException e) { // 192 or 256-bit AES not available
			throw new RuntimeException("AES key length not available:" + AES_KEY_LENGTH, e);
		}

		int skippingDecBytes = 0;
		if (start > 0) {
			long completeBlocks = start / BUFFER_SIZE_ENC;
			long skipSizeToNextBlock = completeBlocks * BUFFER_SIZE_DEC;
			try {
				input.skip(skipSizeToNextBlock);
			} catch (IOException ioe) {
				throw new RuntimeException("error skipping input", ioe);
			}
			skippingDecBytes = (int) (start - (completeBlocks * BUFFER_SIZE_ENC));
		}

		// read data from input into buffer, decrypt and write to output
		byte[] buffer = new byte[BUFFER_SIZE_DEC];
		int numRead;
		byte[] decrypted;
		while ((numRead = read(input, buffer, "payload")) > 0) {
			try {
				decrypted = decrypt.doFinal(buffer, 0, numRead);
				if (decrypted != null) {
					int len = decrypted.length - skippingDecBytes;
					if (lengthToWrite > -1 && bytesWritten + len > lengthToWrite) {
						len = (int) (lengthToWrite - bytesWritten);
					}
					if (len == 0) {
						break;
					}
					output.write(decrypted, skippingDecBytes, len);
					bytesWritten += len;
					skippingDecBytes = 0;
				}
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				throw new RuntimeException("error in doFinal", e);
			} catch (IOException ioe) {
				break;
			}
		}
		try {
			output.flush();
		} catch (IOException e) {
			// ignore
		}
	}

	/**
	 * Reads the length of the decrypted file
	 */
	public static long readDecryptedSize(InputStream input) {

		byte[] size = new byte[LENGTH_PREFIX];
		read(input, size, "size");

		return Long.parseLong(new String(size, StandardCharsets.UTF_8));
	}

	private static int read(InputStream input, byte[] target, String ident) {

		int len = -1;
		try {
			len = input.read(target);
		} catch (IOException e) {
			new RuntimeException("error reading intput", e);
		}
		return len;
	}

	private static void write(OutputStream output, byte[] payload, String ident) {

		try {
			output.write(payload);
		} catch (IOException e) {
			new RuntimeException("error writing output", e);
		}
	}

	/**
	 * A tuple of encryption and authentication keys returned by {@link #keygen}
	 */
	private static class Keys {
		public final SecretKey encryption, authentication;

		public Keys(SecretKey encryption, SecretKey authentication) {
			this.encryption = encryption;
			this.authentication = authentication;
		}
	}

}
