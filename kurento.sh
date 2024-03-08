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
