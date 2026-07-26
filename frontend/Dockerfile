# syntax=docker/dockerfile:1
# Build the Vite app, then serve the static bundle from nginx (which also proxies /api,
# /health and /info to this slot's backend, so the browser stays same-origin).

# Where this UI is served from: "" (the site root) locally, "/neo-03" in the deployed
# stack, where every UI shares one port and is told apart by its path. It is a BUILD
# argument, not an environment variable, because Vite bakes asset URLs into index.html at
# build time and an ALB cannot rewrite paths on the way through.
ARG APP_BASE_PATH=""

FROM node:20-alpine AS build
ARG APP_BASE_PATH
WORKDIR /app
COPY package.json package-lock.json ./
# `npm ci` can fail, print "Exit handler never called!" and still exit 0 — Docker then
# caches the layer as good and the build dies two steps later with `sh: vite: not found`,
# which reads as "the frontend is broken". Fail here instead, where the cause is legible.
RUN --mount=type=cache,target=/root/.npm npm ci && test -x node_modules/.bin/vite
COPY . .
# APP_BASE_PATH -> vite's `base` (asset URLs); VITE_API_BASE -> the prefix src/api.js
# puts in front of every fetch. One input, both halves.
ENV APP_BASE_PATH=${APP_BASE_PATH}
ENV VITE_API_BASE=${APP_BASE_PATH}
RUN npm run build

FROM nginx:1.27-alpine AS runtime
ARG APP_BASE_PATH
# Which backend to proxy to: a compose service name locally, 127.0.0.1 on Fargate where
# both containers share one network namespace.
ENV BACKEND_HOST=backend
# The same prefix again, this time so nginx serves and strips it. Baked as the default
# because it must agree with what the build above wrote into index.html.
ENV BASE_PATH=${APP_BASE_PATH}
# nginx only consults a resolver for NAMES. Docker's embedded DNS locally; on Fargate
# BACKEND_HOST is a literal IP so nothing is resolved, but the directive still has to
# name something that exists — hence the override in the task definition.
ENV RESOLVER=127.0.0.11
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
