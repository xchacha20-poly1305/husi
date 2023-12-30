curl -Lo node.tar.gz https://nodejs.org/dist/latest/node-v21.4.0-linux-x64.tar.gz

echo "d8cd0ec0b78bcbc591e7a4655a92c1c667e64bc434e7a895904dc1fe9442af1d node.tar.gz" | sha256sum -c -

tar xzf node.tar.gz --strip-components=1 -C /usr/local/

rm node.tar.gz

npm install -g pnpm
