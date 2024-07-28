import os
import pathvalidate
import json
import glob

CONFIG_FILE_PATH = 'requests/gnb.json'
SAVED_FILES_PATH = 'saved'

def filterIP(addr):
    try:
        ip, mask = addr.split('/')
    except ValueError:
        raise ValueError('no mask found in : ', addr)
    mask = int(mask)
    if mask < 0 or mask > 32:
        raise ValueError('Invalid mask : ', mask)
    def valid(v):
        v = int(v)
        if v > 255 or v < 0:
            raise ValueError('Invalid value: ', v)
        return str(v)
    l = map(valid, ip.split('.'))
    return '.'.join(l) + f'/{mask}'

def filterPort(port):
    port_id = int(port)
    channel_number = port_id % 10
    port_number = port_id // 10

    if channel_number > 3 or channel_number < 0:
        raise ValueError('invalid channel number')
    if port_number < 0 or port_number > 31:
        raise ValueError('invalid port number')

    return port_id, channel_number, port_number

def filterMac(mac_addr):
    def valid(v):
        vi = int(v, 16)
        if vi < 0 or vi > 255:
            raise ValueError()
        return '{:02x}'.format(vi)
    s = mac_addr.split(':')
    if len(s) != 6:
        raise ValueError()
    l = map(valid, s)
    return ':'.join(l)

def filterName(name):
    if not pathvalidate.is_valid_filename(name):
        raise ValueError
    return name.lower()

def inputWithExit(msg):
    res = input(msg)
    if res.strip() == "":
        exit(0)
    return res

def get_config():
    try:
        with open(CONFIG_FILE_PATH, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        print('No config file was found for the gnb, please provide the following information:')
        
        while True:
            try:
                port_id, _, _ = filterPort(inputWithExit('What port is the gNB connected to ? Please provide a full port ID [port number][channel number] (take a look at stratum.log to see what ports are up) '))
                break
            except ValueError as e:
                print('Please enter a valid port number: ', e)
        
        while True:
            try:
                gnb_mac = filterMac(inputWithExit('What mac address does the gnb use on the interface connected to the switch ? '))
                break
            except ValueError:
                print('Please enter a valid mac address')
        
        while True:
            try:
                switch_mac = filterMac(inputWithExit('What mac address should the switch use on this connection ? '))
                break
            except ValueError:
                print('Please enter a valid mac address')
        
        while True:
            try:
                ip_addr = filterIP(inputWithExit('What IP does the gnb use on this interface ? (ip/32)'))
                break
            except ValueError as e:
                print('Please enter a valid IP address: ', e)
        
        config = {
            "port": port_id,
            "gnb_mac": gnb_mac,
            "switch_mac": switch_mac,
            "gnb_ip": ip_addr,
        }
        
        with open(CONFIG_FILE_PATH, 'w') as f:
            json.dump(config, f)
        
        return config

def get_models():
    for fn in ['filtering', 'forward', 'next']:
        with open(f'requests/model/{fn}.json', 'r') as f:
            yield f.read()

def fill_model(model, *vs):
    for i, v in enumerate(vs):
        model = model.replace(f'${i}', str(v))
    return model

if __name__ == "__main__":
    print("Enter nothing at any point to exit")

    filtering_model, forward_model, next_model = get_models()
    next_id = len(glob.glob(f'requests/{SAVED_FILES_PATH}/filtering-uplink*')) * 2 + 2

    config = get_config()

    print(f"""
GNB connected to port {config['port']}. 
The mac addresses used on this link are gnb:{config['gnb_mac']} switch:{config['switch_mac']}
IP address used by the gnb: {config['gnb_ip']}
""")
    
    while True:
        while True:
            try:
                name = filterName(inputWithExit('Please first provide a name for your new route. If you want to generate an INT route, please use the name "int" :'))
                break
            except ValueError as e:
                print('Please enter a valid IP address')

        while True:
            try:
                ip_addr = filterIP(inputWithExit('What IP address do you want to add ? '))
                break
            except ValueError as e:
                print('Please enter a valid IP address: ', e)

        while True:
            try:
                port_id, channel_number, port_number = filterPort(inputWithExit('What port should the message be sent through ? Please provide a full port ID [port number][channel number] (take a look at stratum.log to see what ports are up) '))
                break
            except ValueError as e:
                print('Please enter a valid port number: ', e)
        
        while True:
            try:
                switch_mac = filterMac(inputWithExit('What mac address should be used by the switch on this connection ? '))
                break
            except ValueError:
                print('Please enter a valid mac address')
        
        while True:
            try:
                pdn_mac = filterMac(inputWithExit('What mac address should the message be sent with to reach the host(s) on your network ? '))
                break
            except ValueError:
                print('Please enter a valid mac address')
        
        if name  == "int":
            for i, recirc_port in enumerate(range(4294967040, 4294967044)):
                with open(f'requests/{SAVED_FILES_PATH}/filtering-int-{i}.json', 'w') as f:
                    f.write(fill_model(filtering_model, recirc_port, '00:00:00:00:00:00'))
        
            with open(f'requests/{SAVED_FILES_PATH}/forward-int.json', 'w') as f:
                f.write(fill_model(forward_model, ip_addr, 1))
            
            with open(f'requests/{SAVED_FILES_PATH}/next-int.json', 'w') as f:
                f.write(fill_model(next_model, port_id, switch_mac, pdn_mac, 1))
        else:
            with open(f'requests/{SAVED_FILES_PATH}/filtering-uplink-{name}.json', 'w') as f:
                f.write(fill_model(filtering_model, config['port'], config['switch_mac']))
            
            with open(f'requests/{SAVED_FILES_PATH}/forward-uplink-{name}.json', 'w') as f:
                f.write(fill_model(forward_model, ip_addr, next_id))
            
            with open(f'requests/{SAVED_FILES_PATH}/next-uplink-{name}.json', 'w') as f:
                f.write(fill_model(next_model, port_id, switch_mac, pdn_mac, next_id))
            
            with open(f'requests/{SAVED_FILES_PATH}/filtering-downlink-{name}.json', 'w') as f:
                f.write(fill_model(filtering_model, port_id, switch_mac))
            
            with open(f'requests/{SAVED_FILES_PATH}/forward-downlink-{name}.json', 'w') as f:
                f.write(fill_model(forward_model, config['gnb_ip'], next_id + 1))
            
            with open(f'requests/{SAVED_FILES_PATH}/next-downlink-{name}.json', 'w') as f:
                f.write(fill_model(next_model, config['port'], config['switch_mac'], config['gnb_mac'], next_id + 1))
        
        print(f'Route created to {ip_addr} through port {port_number}:{channel_number} with mac addresses switch:{switch_mac} pdn:{pdn_mac}')
    