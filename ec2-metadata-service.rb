#!/usr/bin/env ruby

require 'sinatra'
require 'sinatra/reloader'
require 'socket'
require 'ipaddr'
require 'inifile'
require 'time'
require 'tilt/erb'

set :bind, ENV['BIND_ADDR'] || '169.254.169.254'
set :port, '80'

creds_file = '/opt/aws/credentials'
docker_range = IPAddr.new('172.17.42.1/16')

get '/latest/meta-data/local-ipv4' do
  if ENV['LOCAL_ADDR']
    ENV['LOCAL_ADDR']
  else
    ipv4 = (Socket.ip_address_list.select { |a| a.ipv4_private? && !(docker_range === a.ip_address) }).last
    ipv4.ip_address
  end
end

get '/latest/meta-data/local-hostname' do
  `hostname`
end

get '/latest/meta-data/instance-id' do
  'i-local'
end

get '/latest/meta-data/ami-id' do
  'ami-local'
end

get '/latest/meta-data/iam/security-credentials/' do
  'default'
end

get '/latest/meta-data/iam/security-credentials/:role' do
  if File.file?(creds_file)
    inifile = IniFile.load(creds_file)
    if inifile.has_section?('default')
      aws_credentials = {
        code: 'Success',
        last_updated: Time.now.utc.iso8601,
        type: 'AWS-HMAC',
        access_key_id: inifile['default']['aws_access_key_id'],
        secret_access_key: inifile['default']['aws_secret_access_key'],
        token: '',
        expiration: (Time.now.utc + 31622400).iso8601
      }
      render_credentials(aws_credentials)
    else
      halt 500, 'The AWS credentials file must have a default section'
    end
  else
    halt 500, "The AWS credentials file must be located at #{creds_file}"
  end
end

def render_credentials(creds)
  erb :credentials, locals: creds
end

__END__

@@ credentials
{
    "Code": "<%= code %>",
    "LastUpdated": "<%= last_updated %>",
    "Type": "<%= type %>",
    "AccessKeyId": "<%= access_key_id %>",
    "SecretAccessKey": "<%= secret_access_key %>",
    "Token": "<%= token %>",
    "Expiration": "<%= expiration %>"
}
