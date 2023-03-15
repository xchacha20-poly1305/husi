curl -Lo node.tar.gz https://nodejs.org/dist/latest/node-v21.5.0-linux-x64.tar.gz

echo "6e61f81fe1759892fb1f84f62fe470c8d4d6dfc07969af5700f06b4672a9e8d3 node.tar.gz" | sha256sum -c -

tar xzf node.tar.gz --strip-components=1 -C /usr/local/

rm node.tar.gz

npm install -g pnpm
