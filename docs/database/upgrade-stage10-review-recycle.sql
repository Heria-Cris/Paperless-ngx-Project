USE paperless_ngx_project;

ALTER TABLE document
  ADD COLUMN deleted_at DATETIME NULL COMMENT '移入回收站时间' AFTER deleted,
  ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING / APPROVED / REJECTED' AFTER deleted_at,
  ADD COLUMN review_comment VARCHAR(500) NULL COMMENT '审查意见' AFTER review_status,
  ADD COLUMN reviewed_by BIGINT NULL COMMENT '审查人ID' AFTER review_comment,
  ADD COLUMN reviewed_at DATETIME NULL COMMENT '审查时间' AFTER reviewed_by;

CREATE INDEX idx_document_review_status ON document (review_status);
CREATE INDEX idx_document_deleted_at ON document (deleted_at);

UPDATE document
SET review_status = 'APPROVED',
    review_comment = '',
    reviewed_by = 1,
    reviewed_at = COALESCE(updated_at, uploaded_at, NOW())
WHERE review_status IS NULL OR review_status = '';
