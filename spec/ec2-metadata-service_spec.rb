require_relative '../ec2-metadata-service.rb'
require 'rspec'
require 'rack/test'

set :environment, :test

describe 'EC2 Metadata Service' do
  include Rack::Test::Methods

  def app
    Sinatra::Application
  end

  describe 'GET local ipv4 address' do
    it 'should return local address' do
      test_ip = '192.168.42.42'
      allow(Socket).to receive_messages(ip_address_list: [Addrinfo.ip(test_ip)])

      get '/latest/meta-data/local-ipv4'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(test_ip)
    end

    it 'should return last ip in list' do
      test_ip = '192.168.42.42'
      test_ip2 = '192.168.42.43'
      allow(Socket).to receive_messages(ip_address_list: [Addrinfo.ip(test_ip), Addrinfo.ip(test_ip2)])

      get '/latest/meta-data/local-ipv4'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(test_ip2)
    end

    it 'should not return ip in docker range' do
      docker_ip = '172.17.41.1'
      test_ip = '192.168.42.42'
      allow(Socket).to receive_messages(ip_address_list: [Addrinfo.ip(test_ip), Addrinfo.ip(docker_ip)])

      get '/latest/meta-data/local-ipv4'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(test_ip)
    end
  end

  describe 'GET local hostname' do
    it 'should return hostname' do
      local_hostname = `hostname`
      get '/latest/meta-data/local-hostname'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(local_hostname)
    end
  end

  describe 'GET /latest/meta-data/instance-id' do
    it 'should return fake Instance ID' do
      expected_instance_id = 'i-local'

      get '/latest/meta-data/instance-id'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(expected_instance_id)
    end
  end

  describe 'GET /latest/meta-data/ami-id' do
    it 'should return fake AMI ID' do
      expected_ami = 'ami-local'

      get '/latest/meta-data/ami-id'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(expected_ami)
    end
  end

  describe 'GET security credentials' do
    it 'should return default role' do
      default_role = 'default'

      get '/latest/meta-data/iam/security-credentials/'

      expect(last_response.status).to eq 200
      expect(last_response.body).to eq(default_role)
    end
  end

  describe 'GET security credentials for role' do
    creds_file = '/opt/aws/credentials'

    it 'should return credentials for role' do
      allow(File).to receive(:file?).with(creds_file).and_return(true)

      mock_ini_file = double('IniFile')
      allow(mock_ini_file).to receive(:has_section?).with('default').and_return(true)

      # noinspection RubyStringKeysInHashInspection
      mock_default_section = {'aws_access_key_id' => 'key', 'aws_secret_access_key' => 'secret'}
      allow(mock_ini_file).to receive(:[]).with('default').and_return(mock_default_section)

      allow(IniFile).to receive(:load).with(creds_file).and_return(mock_ini_file)

      now = Time.now.utc
      one_year_from_now = now + 31622400
      allow(Time).to receive_messages(now: now)

      get '/latest/meta-data/iam/security-credentials/default'

      expect(last_response.status).to eq 200
      expect(last_response.body).to include('"AccessKeyId": "key"')
      expect(last_response.body).to include('"SecretAccessKey": "secret"')
      expect(last_response.body).to include("\"LastUpdated\": \"#{now.iso8601}\"")
      expect(last_response.body).to include("\"Expiration\": \"#{one_year_from_now.iso8601}\"")
    end

    it 'should return error if creds file missing' do
      allow(File).to receive(:file?).with(creds_file).and_return(false)

      get '/latest/meta-data/iam/security-credentials/default'

      expect(last_response.status).to eq 500
      expect(last_response.body).to eq("The AWS credentials file must be located at #{creds_file}")
    end

    it 'should return error if no default profile in creds file' do
      allow(File).to receive(:file?).with(creds_file).and_return(true)

      mock_ini_file = double('IniFile')
      allow(mock_ini_file).to receive(:has_section?).with('default').and_return(false)

      allow(IniFile).to receive(:load).with(creds_file).and_return(mock_ini_file)

      get '/latest/meta-data/iam/security-credentials/MyNewEC2S3Role'

      expect(last_response.status).to eq 500
      expect(last_response.body).to eq('The AWS credentials file must have a default section')
    end

  end
end  
