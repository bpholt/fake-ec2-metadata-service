FROM alpine:3.5
MAINTAINER Brian Holt <bholt+docker@planetholt.com>

VOLUME ["/opt/aws"]

WORKDIR /opt/fake-ec2-metadata-service
COPY Gemfile Gemfile.lock ec2-metadata-service.rb /opt/fake-ec2-metadata-service/
COPY spec /opt/fake-ec2-metadata-service/spec/

RUN apk update && \
    apk upgrade && \
    apk add ruby ruby-io-console ruby-bundler && \
    apk --update add ca-certificates ruby && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /opt/fake-ec2-metadata-service && \
    bundle install --deployment && \
    bundle exec rspec && \
    rm -rf spec/ vendor/ && \
    bundle install --without test --deployment

EXPOSE 80
ENTRYPOINT ["bundle", "exec", "/opt/fake-ec2-metadata-service/ec2-metadata-service.rb"]

