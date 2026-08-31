####### Build stage #######
FROM node:24-alpine AS builder
WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml ./

# pnpm 11 fails the install if there are unapproved build scripts. They are approved here.
# so that the image does not depend on the local pnpm configuration.
RUN printf 'allowBuilds:\n  "@parcel/watcher": true\n  esbuild: true\n  lmdb: true\n  msgpackr-extract: true\n' > pnpm-workspace.yaml

RUN pnpm install --frozen-lockfile

COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public ./public
COPY src ./src

RUN pnpm run build --configuration production

###### Run stage #######
FROM nginx:1.29-alpine
WORKDIR /usr/share/nginx/html

RUN rm -rf ./*

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist/TheArchivist/browser ./

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
