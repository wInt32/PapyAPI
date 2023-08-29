# PapyAPI
PapyAPI is a minecraft paper plugin for accessing a server over a network.  
***This plugin is insecure, anyone can send requests to the server
without any authentication. USE THIS ON A LOCAL NETWORK ONLY!!!***  

# Use case
PapyAPI is a great tool if you want to run a script to generate something
on a local server, e.g. one could run a script to fetch the time and generate
the text on the server.  

# API
TCP port: 32932  
C is clientbound, S serverbound  
Strings are not null terminated unless specified. Strings prefixed with L are
the first 2 bytes representing the length of the entire message, LL signify the
first 4 bytes of length of the response. Each response consists of a status and
an info message. On success, message can be empty.  
  
Response format:  
C LL "SUCCESS\nInfo\n"  
or  
C LL "FAILURE\nInfo\n"  

## Current functions

### Handshake
1. S "PapyAPI\n"  
2. C "SUCCESS\n"  

### Ping
1. S L "ping"  
2. RESPONSE

### Disconnect
1. S L "disconnect"  
2. RESPONSE  

### Run command  
1. S L "runcmd command"  
2. RESPONSE  

### Set block  
1. S L "setblock world x y z block"  
2. RESPONSE  

### Set block (async)  
1. S L "async\_setblock world x y z block"  
2. RESPONSE  

### Synchronize  
1. S L "sync"  
2. RESPONSE  

