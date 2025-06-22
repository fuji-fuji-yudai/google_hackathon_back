CREATE TABLE public.feedbacks (
    id BIGSERIAL PRIMARY KEY,          -- 主キー
    reflection_id BIGINT NOT NULL,    -- ReflectionデータのID
    feedback TEXT NOT NULL,           -- 生成されたフィードバック
    created_at TIMESTAMP NOT NULL,    -- フィードバック生成日時
    CONSTRAINT fk_reflection_id FOREIGN KEY (reflection_id) REFERENCES public.reflections (id) ON DELETE CASCADE
);

CREATE INDEX idx_feedbacks_reflection_id ON public.feedbacks (reflection_id);