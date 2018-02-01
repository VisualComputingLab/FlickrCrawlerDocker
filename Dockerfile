FROM openjdk:alpine
MAINTAINER Michalis Lazaridis <Michalis.Lazaridis@iti.gr>
RUN apk add --update \
  bash \
  python \
  py-pip \
  && pip install web.py \
  && rm -rf /var/cache/apk/*
RUN mkdir -p /crawler/lib
RUN mkdir -p /crawler/images
COPY ./dist/ /crawler/
COPY ./lib/ /crawler/lib/
COPY ./run_service.py /crawler/
#CMD python /crawler/run_service.py 9876
