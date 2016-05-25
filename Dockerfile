FROM alpine:3.3
MAINTAINER Brian Holt <bholt+docker@planetholt.com>

VOLUME ["/opt/aws"]

RUN apk update && \
    apk upgrade && \
    apk add ruby ruby-io-console ruby-bundler && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /opt/fake-ec2-metadata-service

WORKDIR /opt/fake-ec2-metadata-service
COPY Gemfile Gemfile.lock ec2-metadata-service.rb /opt/fake-ec2-metadata-service/
COPY spec /opt/fake-ec2-metadata-service/spec/

RUN bundle install --deployment && \
    bundle exec rspec && \
    rm -rf spec/ vendor/ && \
    bundle install --without test --deployment

ENTRYPOINT ["bundle", "exec", "/opt/fake-ec2-metadata-service/ec2-metadata-service.rb"]

