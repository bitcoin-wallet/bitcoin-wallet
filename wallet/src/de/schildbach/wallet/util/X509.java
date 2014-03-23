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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.ASN1String;
import org.spongycastle.asn1.x500.AttributeTypeAndValue;
import org.spongycastle.asn1.x500.RDN;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.style.RFC4519Style;

import com.google.common.base.Joiner;

/**
 * @author Andreas Schildbach
 */
public class X509
{
	public static KeyStore trustedCaStore() throws GeneralSecurityException
	{
		try
		{
			// ICS only!
			final KeyStore keystore = KeyStore.getInstance("AndroidCAStore");
			keystore.load(null, null);
			return keystore;
		}
		catch (final IOException x)
		{
			throw new KeyStoreException(x);
		}
	}

	public static TrustAnchor trustAnchor(final List<? extends Certificate> certificateChain) throws GeneralSecurityException
	{
		final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
		final CertPath certificatePath = certificateFactory.generateCertPath(certificateChain);

		final PKIXParameters pkixParams = new PKIXParameters(trustedCaStore());
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

	public static String nameFromCertificate(final X509Certificate certificate)
	{
		final X500Name name = new X500Name(certificate.getSubjectX500Principal().getName());

		String commonName = null, org = null, location = null, country = null;
		for (final RDN rdn : name.getRDNs())
		{
			final AttributeTypeAndValue pair = rdn.getFirst();
			final ASN1ObjectIdentifier type = pair.getType();
			final String val = ((ASN1String) pair.getValue()).getString();

			if (type.equals(RFC4519Style.cn))
				commonName = val;
			else if (type.equals(RFC4519Style.o))
				org = val;
			else if (type.equals(RFC4519Style.l))
				location = val;
			else if (type.equals(RFC4519Style.c))
				country = val;
		}

		if (org != null)
			return Joiner.on(", ").skipNulls().join(org, location, country);
		else
			return commonName;
	}
}
