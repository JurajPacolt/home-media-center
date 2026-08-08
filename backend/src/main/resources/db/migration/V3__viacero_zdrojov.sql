-- Multiple Samba sources at once.
--
-- The schema supported this from V1: both `media_item.source_id` and `scan_run.source_id`
-- already exist, and `ux_media_item_path` covers (source_id, relative_path), so identical
-- paths on two sources are valid. Only name uniqueness was missing: two identically named
-- sources are indistinguishable when listed or selected in a filter.

-- Note: the index covers the plain column, not LOWER(name), because H2 does not support
-- functional indexes (CREATE INDEX accepts only column names). SmbSourceService therefore
-- checks case-insensitive matches through a LOWER(...) query; this index guards against an
-- exact duplicate. Only that service writes to smb_source.
CREATE UNIQUE INDEX ux_smb_source_name ON smb_source (name);

-- The dashboard and library query counts and the latest scan by source.
CREATE INDEX ix_media_item_source ON media_item (source_id, category);
CREATE INDEX ix_scan_run_source ON scan_run (source_id, id);
