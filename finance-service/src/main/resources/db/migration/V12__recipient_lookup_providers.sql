ALTER TABLE bank_lookup_proofs DROP CONSTRAINT bank_lookup_proofs_provider_check;
ALTER TABLE bank_lookup_proofs ADD CONSTRAINT bank_lookup_proofs_provider_check CHECK(provider IN ('PAY2S','BANKLOOKUP'));
