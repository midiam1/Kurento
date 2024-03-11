#!/bin/bash

# Limpio la pantalla y comienzo con el chisme

  clear

# Créditos, si los borras, te buscaré y te nalguearé

  echo " Guión creado por Proyectos Himmeros para Ra+"
  echo " se va a actualizar Linux y a instalar Webmin "

# -------------------------------------------------------------------- #

## Pongo el prompt bonito ::

    echo 'export PS1="\[\e[34m\]\u\[\e[m\]@\[\e[31m\]\h\[\e[m\]:\[\e[32m\]\w\[\e[m\] "' >> $HOME/.profile

## Corrigo la fecha ::

    timedatectl set-timezone America/Caracas

    echo " Actualizo ... "

    sudo apt -y update
    sudo apt -y upgrade

## Instalo algunas herramientas

    sudo apt -y install net-tools

# -------------------------------------------------------------------- #

# Webmin
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

# Instalación de Kurento, será en modo local
## Instalamos y actualizamos algunas cositas

    sudo apt-get -y update ; sudo apt-get install --no-install-recommends gnupg

## Tomado del sitio oficial https://doc-kurento.readthedocs.io/en/latest/user/installation.html#local-installation

    # Get DISTRIB_* env vars.
    source /etc/upstream-release/lsb-release 2>/dev/null || source /etc/lsb-release

    # Add Kurento repository key for apt-get.
    sudo apt-key adv \
    --keyserver hkp://keyserver.ubuntu.com:80 \
    --recv-keys 234821A61B67740F89BFD669FC8A16625AFA7A83

    # Añado Kurento al repositorio.

    echo "deb [arch=amd64] http://ubuntu.openvidu.io/7.0.0 $DISTRIB_CODENAME main" | sudo tee -a /etc/apt/sources.list.d/kurento.list
    
    sudo apt-get -y update ; sudo apt-get -y install --no-install-recommends kurento-media-server
    sudo apt-get -y install maven

## Inicio Kurento

    sudo service kurento-media-server start
    clear

## Comprobación de funcionamiento 

    curl \
        --include \
        --header "Connection: Upgrade" \
        --header "Upgrade: websocket" \
        --header "Host: 127.0.0.1:8888" \
        --header "Origin: 127.0.0.1" \
        "http://127.0.0.1:8888/kurento"

## Servidor NetCap

    sudo apt-get -y update
    sudo apt-get -y install netcat-openbsd
#