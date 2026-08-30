# Abrir Movify en Cursor (repo aparte)

Este branch es el monorepo Movify en la **raíz** (no dentro de vescreendflow).

## 1. Crear el repo vacío en GitHub

En github.com → New repository → nombre **`MOVIFY`** (vacío, sin README).

## 2. En el Mac, carpeta Repositories

```bash
mkdir -p ~/Repositories   # o la ruta que ya creaste
cd ~/Repositories

git clone -b cursor/movify-repo-standalone-01e2 --single-branch \
  https://github.com/crios82-max/vescreendflow.git MOVIFY

cd MOVIFY
git remote remove origin
git remote add origin https://github.com/crios82-max/MOVIFY.git
git branch -M main
git push -u origin main
```

Si la carpeta `MOVIFY` ya existe vacía:

```bash
cd ~/Repositories/MOVIFY
git init
git remote add vescreen https://github.com/crios82-max/vescreendflow.git
git fetch vescreen cursor/movify-repo-standalone-01e2
git checkout -b main vescreen/cursor/movify-repo-standalone-01e2
git remote add origin https://github.com/crios82-max/MOVIFY.git
git push -u origin main
git remote remove vescreen
```

## 3. Abrir en Cursor

**File → Open Folder** → `~/Repositories/MOVIFY`

Queda como proyecto/repo aparte en Cursor.
