# JS dependencies
FROM node:14.7-stretch AS node-deps
WORKDIR /app
COPY package.json package.json
RUN npm install

# Main build
FROM clojure:openjdk-11-tools-deps AS build
WORKDIR /app
COPY --from=node-deps /app/node_modules /app/node_modules

COPY deps.edn deps.edn
RUN clojure -A:build:prod -M -e ::ok   # preload and cache dependencies, only reruns if deps.edn changes

COPY .git .git
COPY shadow-cljs.edn shadow-cljs.edn
COPY src src
COPY src-build src-build
COPY src-prod src-prod
COPY resources resources

RUN clojure -X:build:prod uberjar :build/jar-name app.jar

CMD java -cp app.jar clojure.main -m prod
