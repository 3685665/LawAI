from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_provider: str = Field(default="local", validation_alias=AliasChoices("LAWAI_AI_PROVIDER", "AI_PROVIDER"))
    database_url: str = Field(
        default="postgresql://lawai:lawai@localhost:5433/lawai",
        validation_alias=AliasChoices("DATABASE_URL", "LAWAI_DATABASE_URL"),
    )
    embedding_dimensions: int = Field(
        default=384,
        validation_alias=AliasChoices("LAWAI_EMBEDDING_DIMENSIONS", "EMBEDDING_DIMENSIONS"),
    )
    openai_api_key: str | None = Field(default=None, validation_alias="OPENAI_API_KEY")
    openai_embedding_model: str = Field(default="text-embedding-3-small", validation_alias="OPENAI_EMBEDDING_MODEL")
    google_api_key: str | None = Field(default=None, validation_alias=AliasChoices("GOOGLE_API_KEY", "GEMINI_API_KEY"))
    gemini_embedding_model: str = Field(default="models/embedding-001", validation_alias="GEMINI_EMBEDDING_MODEL")
    ollama_base_url: str = Field(default="http://localhost:11434", validation_alias="OLLAMA_BASE_URL")
    ollama_embedding_model: str = Field(default="nomic-embed-text", validation_alias="OLLAMA_EMBEDDING_MODEL")
    chunk_size: int = Field(default=1200, validation_alias="PDF_CHUNK_SIZE")
    chunk_overlap: int = Field(default=150, validation_alias="PDF_CHUNK_OVERLAP")
    file_batch_size: int = Field(default=128, validation_alias="PDF_FILE_BATCH_SIZE")
    embedding_batch_size: int = Field(default=32, validation_alias="PDF_EMBEDDING_BATCH_SIZE")
    min_extracted_characters: int = Field(default=40, validation_alias="PDF_MIN_EXTRACTED_CHARACTERS")

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()

