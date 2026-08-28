const path = require('path');

const root = __dirname;

module.exports = {
  apps: [
    {
      name: 'ride-api',
      cwd: root,
      script: 'npm',
      args: 'run start -w @ride-app/api',
      env: {
        NODE_ENV: 'production',
      },
      max_restarts: 10,
      restart_delay: 3000,
    },
    {
      name: 'ride-passenger',
      cwd: root,
      script: 'npm',
      args: 'run preview -w @ride-app/passenger -- --host 0.0.0.0 --port 5174',
      env: {
        NODE_ENV: 'production',
      },
      max_restarts: 10,
      restart_delay: 3000,
    },
    {
      name: 'ride-driver',
      cwd: root,
      script: 'npm',
      args: 'run preview -w @ride-app/driver -- --host 0.0.0.0 --port 5175',
      env: {
        NODE_ENV: 'production',
      },
      max_restarts: 10,
      restart_delay: 3000,
    },
  ],
};
