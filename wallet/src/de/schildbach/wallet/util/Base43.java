/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.util;

import java.math.BigInteger;
import java.util.Arrays;

import com.google.bitcoin.core.Utils;

/**
 * Base43, derived from Base58
 */
public class Base43
{
	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$*+-./:";
	private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());

	public static String encode(byte[] input)
	{
		// TODO: This could be a lot more efficient.
		BigInteger bi = new BigInteger(1, input);
		StringBuffer s = new StringBuffer();
		while (bi.compareTo(BASE) >= 0)
		{
			BigInteger mod = bi.mod(BASE);
			s.insert(0, ALPHABET.charAt(mod.intValue()));
			bi = bi.subtract(mod).divide(BASE);
		}
		s.insert(0, ALPHABET.charAt(bi.intValue()));
		// Convert leading zeros too.
		for (byte anInput : input)
		{
			if (anInput == 0)
				s.insert(0, ALPHABET.charAt(0));
			else
				break;
		}
		return s.toString();
	}

	public static byte[] decode(String input) throws IllegalArgumentException
	{
		byte[] bytes = decodeToBigInteger(input).toByteArray();
		// We may have got one more byte than we wanted, if the high bit of the next-to-last byte was not zero. This
		// is because BigIntegers are represented with twos-compliment notation, thus if the high bit of the last
		// byte happens to be 1 another 8 zero bits will be added to ensure the number parses as positive. Detect
		// that case here and chop it off.
		boolean stripSignByte = bytes.length > 1 && bytes[0] == 0 && bytes[1] < 0;
		// Count the leading zeros, if any.
		int leadingZeros = 0;
		for (int i = 0; input.charAt(i) == ALPHABET.charAt(0); i++)
		{
			leadingZeros++;
		}
		// Now cut/pad correctly. Java 6 has a convenience for this, but Android can't use it.
		byte[] tmp = new byte[bytes.length - (stripSignByte ? 1 : 0) + leadingZeros];
		System.arraycopy(bytes, stripSignByte ? 1 : 0, tmp, leadingZeros, tmp.length - leadingZeros);
		return tmp;
	}

	public static BigInteger decodeToBigInteger(String input) throws IllegalArgumentException
	{
		BigInteger bi = BigInteger.valueOf(0);
		// Work backwards through the string.
		for (int i = input.length() - 1; i >= 0; i--)
		{
			int alphaIndex = ALPHABET.indexOf(input.charAt(i));
			if (alphaIndex == -1)
			{
				throw new IllegalArgumentException("Illegal character " + input.charAt(i) + " at " + i);
			}
			bi = bi.add(BigInteger.valueOf(alphaIndex).multiply(BASE.pow(input.length() - 1 - i)));
		}
		return bi;
	}

	/**
	 * Uses the checksum in the last 4 bytes of the decoded data to verify the rest are correct. The checksum is removed
	 * from the returned data.
	 *
	 * @throws IllegalArgumentException
	 *             if the input is not base 43 or the checksum does not validate.
	 */
	public static byte[] decodeChecked(String input) throws IllegalArgumentException
	{
		byte[] tmp = decode(input);
		if (tmp.length < 4)
			throw new IllegalArgumentException("Input too short");
		byte[] checksum = new byte[4];
		System.arraycopy(tmp, tmp.length - 4, checksum, 0, 4);
		byte[] bytes = new byte[tmp.length - 4];
		System.arraycopy(tmp, 0, bytes, 0, tmp.length - 4);
		tmp = Utils.doubleDigest(bytes);
		byte[] hash = new byte[4];
		System.arraycopy(tmp, 0, hash, 0, 4);
		if (!Arrays.equals(hash, checksum))
			throw new IllegalArgumentException("Checksum does not validate");
		return bytes;
	}
}
