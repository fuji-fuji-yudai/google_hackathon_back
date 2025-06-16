-- DROP TABLE IF EXISTS reflection;

CREATE TABLE reflection (
  id                 SERIAL         PRIMARY KEY,
  user_id            INT,
  reflection_date    DATE,
  activity           TEXT,
  achievement        TEXT,
  improvement_points TEXT
);