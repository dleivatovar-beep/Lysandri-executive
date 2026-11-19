ALTER TABLE documento_chunk ADD COLUMN embedding_local vector;
ALTER TABLE documento_chunk ALTER COLUMN embedding DROP NOT NULL;
ALTER TABLE documento_chunk ADD CONSTRAINT documento_chunk_embedding_required
    CHECK (embedding IS NOT NULL OR embedding_local IS NOT NULL);
