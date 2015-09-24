#!/usr/bin/env ruby

require 'sinatra'
require 'sinatra/reloader'
require 'socket'

set :bind, '169.254.169.254'
set :port, '80'

get '/latest/meta-data/local-ipv4' do
    ipv4 = (Socket.ip_address_list.select {|a| a.ipv4_private? && a.ip_address != "172.17.42.1" }).last
    ipv4.ip_address
end

get '/latest/meta-data/local-hostname' do
    `hostname`
end
