# Stage 1: Build the React application
# This stage uses a heavy Node image to compile the code
FROM node:22-alpine AS build

WORKDIR /app

# Copy package files first to leverage Docker layer caching
COPY package*.json ./
RUN npm install

# Copy the rest of the code and build the production static files
COPY . .
RUN npm run build

# Stage 2: Serve the static files with Nginx
# This stage results in a tiny, high-performance image
FROM nginx:alpine

# Copy the NGINX config into place
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the compiled 'build' folder from the previous stage to Nginx's web root
COPY --from=build /app/build /usr/share/nginx/html

# Nginx serves on port 80 by default
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
