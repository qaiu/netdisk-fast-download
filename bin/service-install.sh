cp ./lz-api.service  /etc/systemd/system/

# 重新加载 systemd
systemctl daemon-reload

# 运行服务
systemctl start netdisk-fast-download

# 在系统启动时启动服务
systemctl enable netdisk-fast-download
