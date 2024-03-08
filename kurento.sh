#!/bin/bash

# Limpio la pantalla y comienzo con el chisme
  clear

# Créditos, si los borras, te buscaré y te nalguearé
  echo " Script creado por Proyectos Himmeros "
  echo " se va a actualizar Linux y a instalar Webmin "
# -------------------------------------------------------------------- #

## Pongo el prompt bonito ::

echo 'export PS1="\[\e[34m\]\u\[\e[m\]@\[\e[31m\]\h\[\e[m\]:\[\e[32m\]\w\[\e[m\]"' >> $HOME/.profile

## Corrigo la fecha ::

timedatectl set-timezone America/Caracas

  echo " Actualizo ... "

  sudo apt -y update
  sudo apt -y upgrade
    ## Instalo algunas herramientas
  sudo apt -y install net-tools

# -------------------------------------------------------------------- #

## Importante ::

    clear
    echo ' Instalo certificados para que la instalación de Webmin no arroje error '
    sudo apt install ca-certificates -y

## Configuro los repositorios de webmin

    sudo apt -y install curl
    sudo curl -o setup-repos.sh https://raw.githubusercontent.com/webmin/webmin/master/setup-repos.sh
    sudo sh setup-repos.sh

## Instalo Webmin

    sudo apt -y install --install-recommends webmin
    echo ' Una vez instalado webmin se debe abrir el puerto 10000 para que acepte las conexiones . '

## Instalación de Docker

## Instalo certificados

    sudo apt-get install apt-transport-https ca-certificates curl gnupg-agent software-properties-common
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    sudo apt-key fingerprint 0EBFCD88

## Añado el repositorio de Docker ::

    sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"

## Con esto instalamos la última versión de Docker ::

    apt -y install docker-ce docker-ce-cli containerd.io

## Verificamos que Docker esté instalado ::

    docker run hello-world

## Instalación de docker-compose

    sudo apt -y update
    sudo apt -y install docker-compose
    docker-compose --version

## Instalación Portainer

## Por seguridad y control todo será instalado en /home

    mkdir /home/portainer
    cd /home/portainer

    sudo docker volume create portainer_data
    sudo docker run -d -p 8000:8000 -p 9443:9443 --name portainer --restart=always -v /var/run/docker.sock:/var/run/docker.sock -v portainer_data:/data portainer/portainer-ce:latest

    echo ' Posteriormente se debe abrir el puerto 9443 para poder acceder a Portainer . '
    echo ' Una vez alcanzado el acceso se debe registrar al usuario administrativo . '

## Instalación de docker Kuranto 

    sudo docker pull kurento/kurento-media-server:7.0.0
    sudo docker run -d --name kurento --network host kurento/kurento-media-server:7.0.0
