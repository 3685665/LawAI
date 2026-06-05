from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_provider: str = Field(default="local", validation_alias=AliasChoices("LAWAI_AI_PROVIDER", "AI_PROVIDER"))
    vector_store: str = Field(default="json", validation_alias=AliasChoices("LAWAI_VECTOR_STORE", "VECTOR_STORE"))
    database_url: str = Field(
        default="postgresql://lawai:lawai@localhost:5433/lawai",
        validation_alias=AliasChoices("DATABASE_URL", "LAWAI_DATABASE_URL"),
    )
    openai_api_key: str | None = Field(default=None, validation_alias="OPENAI_API_KEY")
    openai_chat_model: str = Field(default="gpt-4o-mini", validation_alias="OPENAI_CHAT_MODEL")
    openai_embedding_model: str = Field(default="text-embedding-3-small", validation_alias="OPENAI_EMBEDDING_MODEL")
    google_api_key: str | None = Field(default=None, validation_alias=AliasChoices("GOOGLE_API_KEY", "GEMINI_API_KEY"))
    gemini_chat_model: str = Field(default="gemini-2.5-flash", validation_alias="GEMINI_CHAT_MODEL")
    gemini_embedding_model: str = Field(default="models/embedding-001", validation_alias="GEMINI_EMBEDDING_MODEL")
    ollama_base_url: str = Field(default="http://localhost:11434", validation_alias="OLLAMA_BASE_URL")
    ollama_chat_model: str = Field(default="qwen2.5:3b", validation_alias="OLLAMA_CHAT_MODEL")
    ollama_embedding_model: str = Field(default="nomic-embed-text", validation_alias="OLLAMA_EMBEDDING_MODEL")
    embedding_dimensions: int = Field(default=384, validation_alias=AliasChoices("LAWAI_EMBEDDING_DIMENSIONS", "EMBEDDING_DIMENSIONS"))
    min_vector_similarity: float = Field(default=0.12, validation_alias=AliasChoices("LAWAI_MIN_VECTOR_SIMILARITY", "MIN_VECTOR_SIMILARITY"))
    min_keyword_overlap_ratio: float = Field(default=0.45, validation_alias=AliasChoices("LAWAI_MIN_KEYWORD_OVERLAP_RATIO", "MIN_KEYWORD_OVERLAP_RATIO"))
    min_keyword_overlap_terms: int = Field(default=2, validation_alias=AliasChoices("LAWAI_MIN_KEYWORD_OVERLAP_TERMS", "MIN_KEYWORD_OVERLAP_TERMS"))
    max_upload_mb: int = Field(default=25, validation_alias="MAX_UPLOAD_MB")

    model_config = SettingsConfigDict(
        env_file=".env",
        extra="ignore",
    )


settings = Settings()
