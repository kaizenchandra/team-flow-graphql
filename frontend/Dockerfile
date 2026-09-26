FROM node:24.8.0-alpine AS build
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
COPY backend/src/main/resources/graphql/ /src/backend/src/main/resources/graphql/
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28.0-alpine
COPY --chown=101:101 deploy/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/frontend/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
