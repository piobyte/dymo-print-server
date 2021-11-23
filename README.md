# dymo-print-server
RESTful Web Service for the use of Dymo labeling machines (LabelManager PnP).
The project is build on top of the __dymo-print__ Java library.

// TODO link to dymo-print project


## Supported printers
- DYMO LabelManager PnP

## Supported Platforms
- Linux
- Mac OS
- Windows

## Setup

### Linux
To be able to connect to the printer under Linux you need to change the mode of the printer. 
The following instructions are based on the following post: https://www.draisberghof.de/usb_modeswitch/bb/viewtopic.php?t=947

You can use the tools __udev__ and __modeswitch__ to work with the LabelManager PNP under Linux.
#### Ubuntu or Debian based System
1. Create udev rule  
   `sudo vim /etc/udev/rules.d/91-dymo-labelmanager-pnp.rules` and add the following content:
  
        # DYMO LabelManager PNP
        SUBSYSTEMS=="usb", ATTRS{idVendor}=="0922", ATTRS{idProduct}=="1001", RUN+="/usr/sbin/usb_modeswitch -c /etc/usb_modeswitch.d/dymo-labelmanager-pnp.conf"
        SUBSYSTEMS=="usb", ATTRS{idVendor}=="0922", ATTRS{idProduct}=="1002", MODE="0660", GROUP="plugdev"
2. Create __modeswitch__ configuration  
   `sudo vim /etc/usb_modeswitch.d/dymo-labelmanager-pnp.conf` and add the following content:
  
        # Dymo LabelManager PnP
        DefaultVendor= 0x0922
        DefaultProduct=0x1001
        
        TargetVendor=  0x0922
        TargetProduct= 0x1002
        
        MessageEndpoint= 0x01
        ResponseEndpoint=0x01
        
        MessageContent="1b5a01"
3. Unplug the printer and reload udev service: `sudo systemctl restart udev.service`
4. Done
#### Arch Linux
1. Install __modeswitch__ and create the configuration directory.  
  `sudo pacman -S usb_modeswitch`  
  `sudo mkdir /etc/usb_modeswitch.d/`
2. Create udev rule  
  `sudo vim /etc/udev/rules.d/91-dymo-labelmanager-pnp.rules` and add the following content:  
  
        # DYMO LabelManager PNP
        SUBSYSTEMS=="usb", ATTRS{idVendor}=="0922", ATTRS{idProduct}=="1001", RUN+="/usr/sbin/usb_modeswitch -c /etc/usb_modeswitch.d/dymo-labelmanager-pnp.conf"
        SUBSYSTEMS=="usb", ATTRS{idVendor}=="0922", ATTRS{idProduct}=="1002", MODE="0666"
3. Create __modeswitch__ configuration  
  `sudo vim /etc/usb_modeswitch.d/dymo-labelmanager-pnp.conf` and add the following content:
  
        # Dymo LabelManager PnP
        DefaultVendor= 0x0922
        DefaultProduct=0x1001
        
        TargetVendor=  0x0922
        TargetProduct= 0x1002
        
        MessageEndpoint= 0x01
        ResponseEndpoint=0x01
        
        MessageContent="1b5a01"
4. Unplug the printer and reload udev service: `sudo udevadm control --reload`
5. Done

### Mac OS

Just install the latest DYMO Label software and you're done 
(e.g. https://download.dymo.com/dymo/Software/Mac/DLS8Setup.8.7.4.dmg).

### Windows

Just install the latest DYMO Connect software and you're done (e.g. https://download.dymo.com/dymo/Software/DYMO%20Connect%201.4.2/DCDSetup1.4.2.82.exe).
