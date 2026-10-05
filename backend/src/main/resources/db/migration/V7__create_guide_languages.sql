CREATE TABLE guide_languages (
                                 guide_language_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                 guide_id BIGINT NOT NULL,

                                 language_name VARCHAR(50) NOT NULL,

                                 CONSTRAINT fk_guide_languages_guide
                                     FOREIGN KEY (guide_id)
                                         REFERENCES guides(guide_id)
                                         ON DELETE CASCADE,

                                 UNIQUE KEY uq_guide_language (guide_id, language_name),

                                 INDEX idx_guide_languages_guide (guide_id)
);