SUPPORTED SPECIFICATIONS

BIP-13: Address format for pay-to-script-hash
https://github.com/bitcoin/bips/blob/master/bip-0013.mediawiki

BIP-14: Protocol version and user agent
https://github.com/bitcoin/bips/blob/master/bip-0014.mediawiki

BIP-21: URI scheme for making Bitcoin payments
https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki

BIP-31: Pong message
https://github.com/bitcoin/bips/blob/master/bip-0031.mediawiki

BIP-32: Hierarchical deterministic wallets
https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki

BIP-35: Mempool message
https://github.com/bitcoin/bips/blob/master/bip-0035.mediawiki

BIP-37: Connection bloom filtering
https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki

BIP-38: Passphrase-protected private key
https://github.com/bitcoin/bips/blob/master/bip-0038.mediawiki

BIP-43: Purpose field for deterministic wallets
https://github.com/bitcoin/bips/blob/master/bip-0043.mediawiki

BIP-66: Strict DER signatures
https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki

BIP-70: Payment protocol
https://github.com/bitcoin/bips/blob/master/bip-0070.mediawiki

BIP-71: Payment protocol MIME types
https://github.com/bitcoin/bips/blob/master/bip-0071.mediawiki

RFC 6979: Deterministic usage of ECDSA
https://tools.ietf.org/html/rfc6979


UNSUPPORTED OR PARTIALLY SUPPORTED SPECIFICATIONS

BIP—44: Multi-account hierarchy for deterministic wallets
https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
By deliberate choice, we don't support multiple accounts per wallet. As BIP-44 requires supporting
multiple accounts, we are using BIP-32 instead. This implies wallets can't be shared between
BIP-32 and BIP-44 compatible wallets, as they would see a different transaction history for the
same seed.

BIP-72: "bitcoin:" URI extensions for payment protocol
https://github.com/bitcoin/bips/blob/master/bip-0072.mediawiki
The spec is supported, except the "...it should ignore the bitcoin address/amount/label/message in
the URI..." part of the recommendation. Important: If you use the request parameter, you have one
of the following choices. If you don't follow one of those, your linked payment request won't be
accepted.
1. Supply an address and optionally an "amount" parameter, with their values exactly matching the
   respective values from the linked payment request message. This means there can be only one
   output in PaymentDetails.outputs and that output can only contain an Output.script of type
   pay-to-pubkey, pay-to-pubkey-hash or pay-to-script-hash. Note you should add these parameters
   anyway for backwards compatibility to wallets that don't support the payment protocol.
2. Supply an "h" parameter, which contains the unpadded base64url-encoded SHA-256 hash of the
   linked payment request bytes.


(these lists are not exhaustive)
