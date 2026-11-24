#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Crea un usuario administrador inicial en Firebase Auth y Firestore para pruebas y desarrollo.
"""

import sys
import firebase_admin
from firebase_admin import credentials, auth, firestore

def main():
    email = "admin@instituto.es"
    password = "AdminPassword123!"
    nombre = "Jefa de Departamento"

    cred = credentials.Certificate("scripts/serviceAccountKey.json")
    firebase_admin.initialize_app(cred)
    db = firestore.client()

    print(f"--> Creando o actualizando usuario: {email}")

    # Verificar si ya existe en Auth
    try:
        user = auth.get_user_by_email(email)
        print(f"  Usuario ya existe en Auth con UID: {user.uid}")
    except auth.UserNotFoundError:
        user = auth.create_user(
            email=email,
            password=password,
            display_name=nombre
        )
        print(f"  Usuario creado en Auth con UID: {user.uid}")

    # Crear o actualizar documento en Firestore
    user_doc_ref = db.collection("usuarios").document(user.uid)
    user_data = {
        "nombre": nombre,
        "email": email,
        "rol": "ADMIN",
        "activo": True
    }
    user_doc_ref.set(user_data)
    print(f"  Documento usuarios/{user.uid} guardado en Firestore con rol ADMIN.")
    print("\n--> USUARIO ADMIN LISTO:")
    print(f"    Email: {email}")
    print(f"    Password: {password}")
    print(f"    Rol: ADMIN")

if __name__ == "__main__":
    main()
