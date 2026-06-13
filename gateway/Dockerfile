FROM node:22-alpine

WORKDIR /app
ENV NODE_ENV=production

# Install only production deps using the lockfile (reproducible build).
COPY package*.json ./
RUN npm ci --omit=dev

# App source (node_modules and .env excluded via .dockerignore).
COPY . .

EXPOSE 8088
USER node

# Liveness: /health is public (no token required).
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8088/health || exit 1

CMD ["node", "gateway.mjs"]
