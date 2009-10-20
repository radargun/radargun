#!/usr/bin/python

import sys
import re

ip_address_file = sys.argv[1]
slave_prefix = sys.argv[2]

exp = re.compile('^[0-9]*\.[0-9]*\.[0-9]*\.([0-9]*)$')

# Comparator for IP addresses
def compare_ips(a, b):
  if a == b:
    return 0
  n_a = int(exp.match(a).group(1))
  n_b = int(exp.match(b).group(1))
  return n_a - n_b


f = open(ip_address_file)
ips = []
try:
  l = f.readline()
  while l:
    trimmed = l.strip()
    if trimmed not in ips:
      ips.append(trimmed)
    l = f.readline()
finally:
  f.close() 

counter=1
for ip in sorted(ips, compare_ips):
  print "%s %s%s" % (ip, slave_prefix, counter)
  counter += 1
