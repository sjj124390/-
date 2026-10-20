FROM mysql:8.0
LABEL org.opencontainers.image.source="https://github.com/sjj124390/-"
COPY database/schema.sql /docker-entrypoint-initdb.d/001-schema.sql
