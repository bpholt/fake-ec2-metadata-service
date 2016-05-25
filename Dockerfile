FROM alpine:3.3
MAINTAINER Brian Holt <bholt+docker@planetholt.com>

RUN apk update && \
    apk upgrade && \
    apk add ruby ruby-io-console ruby-bundler && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /opt/fake-ec2-metadata-service

WORKDIR /opt/fake-ec2-metadata-service
COPY Gemfile Gemfile.lock ec2-metadata-service.rb /opt/fake-ec2-metadata-service/

RUN bundle install

ENTRYPOINT ["/opt/fake-ec2-metadata-service/ec2-metadata-service.rb"]

