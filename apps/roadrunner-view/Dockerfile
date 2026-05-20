# STAGE 1: BUILD THE REACT APPLICATION
# This stage uses a heavy Node image to compile the code
FROM node:22.22.3-alpine3.23 AS build

WORKDIR /app

# Copy package files first to leverage Docker layer caching
COPY package*.json ./
RUN npm install

# Copy the rest of the code and build the production static files
COPY . .
RUN npm run build

# STAGE 2: SERVE THE STATIC FILES WITH NGINX
# This stage results in a tiny, high-performance image
FROM nginx:1.31.0-alpine3.23

# Copy the NGINX config into place
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the compiled 'build' folder from the previous stage to Nginx's web root
COPY --from=build /app/build /usr/share/nginx/html

# Nginx serves on port 80 by default
EXPOSE 80

# Start the server
CMD ["nginx", "-g", "daemon off;"]
