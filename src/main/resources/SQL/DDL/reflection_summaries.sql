CREATE TABLE reflection_summaries (
    id BIGSERIAL PRIMARY KEY, -- 主キー
    user_id BIGINT NOT NULL, -- ユーザーID
    year_month VARCHAR(7) NOT NULL, -- 年月 (例: "2023-10")
    activity_summary TEXT NOT NULL, -- 活動内容の要約
    achievement_summary TEXT NOT NULL, -- 達成事項の要約
    improvement_summary TEXT NOT NULL, -- 改善点の要約
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 作成日時
    CONSTRAINT unique_user_year_month UNIQUE (user_id, year_month) -- ユニーク制約
);

CREATE INDEX idx_user_id_year_month ON monthly_summaries (user_id, year_month);