-- Viacero Samba zdrojov naraz.
--
-- Schéma to uniesla už od V1 — `media_item.source_id` aj `scan_run.source_id` tam sú
-- a index `ux_media_item_path` je nad dvojicou (source_id, relative_path), takže rovnaká
-- cesta na dvoch zdrojoch je v poriadku. Chýbala len jednoznačnosť názvu: keď sa zdroje
-- vypisujú v zozname a vyberajú vo filtri, dva rovnako pomenované sú na nerozoznanie.

-- Pozor: index je nad holým stĺpcom, nie nad LOWER(name) — H2 funkcionálne indexy nevie
-- (CREATE INDEX prijíma len názvy stĺpcov). Zhodu bez ohľadu na veľkosť písmen preto
-- kontroluje SmbSourceService dotazom cez LOWER(...); tento index je poistka proti
-- presnému duplikátu. Zápis do smb_source ide výhradne cez tú službu.
CREATE UNIQUE INDEX ux_smb_source_name ON smb_source (name);

-- Dashboard aj knižnica sa pýtajú na počty a posledný sken po zdrojoch.
CREATE INDEX ix_media_item_source ON media_item (source_id, category);
CREATE INDEX ix_scan_run_source ON scan_run (source_id, id);
