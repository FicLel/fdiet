# fdiet UI: the Vue build served by nginx, which proxies /api to the backend (as Vite does in dev).
# Build:  docker build -t fdiet-ui UI
FROM node:22-alpine AS build
WORKDIR /ui
RUN corepack enable && corepack prepare pnpm@10.14.0 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /ui/dist /usr/share/nginx/html
EXPOSE 80
