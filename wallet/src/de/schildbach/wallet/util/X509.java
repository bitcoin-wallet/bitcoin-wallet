/*
 * Copyright 2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.util;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public class X509
{
	public static TrustAnchor trustAnchor(final List<? extends Certificate> certificateChain, final KeyStore trustedKeyStore)
			throws GeneralSecurityException
	{
		final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
		final CertPath certificatePath = certificateFactory.generateCertPath(certificateChain);

		final PKIXParameters pkixParams = new PKIXParameters(trustedKeyStore);
		pkixParams.setRevocationEnabled(false);

		final CertPathValidator pathValidator = CertPathValidator.getInstance("PKIX");

		try
		{
			final PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) pathValidator.validate(certificatePath, pkixParams);
			return result.getTrustAnchor();
		}
		catch (final CertPathValidatorException x)
		{
			return null;
		}
	}
}
