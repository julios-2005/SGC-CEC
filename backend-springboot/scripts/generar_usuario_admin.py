#!/usr/bin/env python3
"""Genera una contraseña temporal aleatoria y su hash BCrypt para dar de alta un usuario.

Uso:   pip install bcrypt && python3 scripts/generar_usuario_admin.py
Pegar SOLO el hash en la plantilla SQL; entregar la contraseña al usuario por un canal seguro.
La contraseña cumple la política vigente (mínimo 8 caracteres, una mayúscula y un dígito).
Spring Security acepta el prefijo $2b$ que genera esta librería.
"""
import secrets
import string

import bcrypt

ALFABETO = string.ascii_letters + string.digits
clave = "".join(secrets.choice(ALFABETO) for _ in range(14)) + secrets.choice(string.ascii_uppercase) + secrets.choice(string.digits)
hash_bcrypt = bcrypt.hashpw(clave.encode(), bcrypt.gensalt(10)).decode()

print(f"Contraseña temporal: {clave}")
print(f"Hash BCrypt:         {hash_bcrypt}")
