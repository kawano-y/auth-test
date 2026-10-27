-- テーブルは JPA（Hibernate）が Entity から自動生成し、その後にこのファイルが実行される。
-- H2 インメモリなので起動のたびに空から作り直される。
-- id は自動採番に任せる（departments は投入順に 1, 2, 3 になる）。

INSERT INTO departments (name) VALUES ('営業1課');
INSERT INTO departments (name) VALUES ('営業2課');
INSERT INTO departments (name) VALUES ('管理部');

-- 営業1課（department_id = 1）
INSERT INTO estimates (title, amount, department_id, created_at) VALUES ('A社 基幹システム改修', 12000000, 1, '2026-09-01 10:00:00');
INSERT INTO estimates (title, amount, department_id, created_at) VALUES ('B社 保守契約更新', 3600000, 1, '2026-09-15 14:30:00');
INSERT INTO estimates (title, amount, department_id, created_at) VALUES ('C社 データ移行支援', 4800000, 1, '2026-10-05 09:15:00');

-- 営業2課（department_id = 2）
INSERT INTO estimates (title, amount, department_id, created_at) VALUES ('D社 ECサイト構築', 25000000, 2, '2026-08-20 11:00:00');
INSERT INTO estimates (title, amount, department_id, created_at) VALUES ('E社 クラウド移行', 9800000, 2, '2026-10-10 16:45:00');

-- 管理部（department_id = 3）の見積はなし
