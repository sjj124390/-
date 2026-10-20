FROM nginx:stable-alpine
LABEL org.opencontainers.image.source="https://github.com/sjj124390/-"
COPY frontend/ /usr/share/nginx/html/
COPY deploy/docker/config.js /usr/share/nginx/html/js/config.js
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
