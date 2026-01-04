#!/usr/bin/env node

const path = require("path");
const fs = require("fs");
const zlib = require("zlib");
const { promisify } = require("util");

const gzip = promisify(zlib.gzip);
const readdir = promisify(fs.readdir);
const stat = promisify(fs.stat);
const readFile = promisify(fs.readFile);
const writeFile = promisify(fs.writeFile);

// 递归压缩目录下的所有文件
async function compressDirectory(dirPath, threshold = 1024) {
  if (!fs.existsSync(dirPath)) {
    console.warn(`目录不存在: ${dirPath}`);
    return;
  }

  const files = await readdir(dirPath, { withFileTypes: true });
  let compressedCount = 0;
  let totalOriginalSize = 0;
  let totalCompressedSize = 0;
  
  for (const file of files) {
    const filePath = path.join(dirPath, file.name);
    
    if (file.isDirectory()) {
      await compressDirectory(filePath, threshold);
    } else if (file.isFile()) {
      const stats = await stat(filePath);
      // 只压缩超过阈值且不是已压缩的文件
      if (stats.size > threshold && !filePath.endsWith('.gz') && !filePath.endsWith('.map')) {
        try {
          const content = await readFile(filePath);
          const compressed = await gzip(content);
          await writeFile(filePath + '.gz', compressed);
          compressedCount++;
          totalOriginalSize += stats.size;
          totalCompressedSize += compressed.length;
          console.log(`✓ ${file.name} (${(stats.size / 1024).toFixed(2)}KB -> ${(compressed.length / 1024).toFixed(2)}KB)`);
        } catch (error) {
          console.warn(`⚠ 压缩失败: ${filePath}`, error.message);
        }
      }
    }
  }
  
  if (compressedCount > 0) {
    console.log(`\n压缩完成: ${compressedCount} 个文件`);
    console.log(`原始大小: ${(totalOriginalSize / 1024 / 1024).toFixed(2)}MB`);
    console.log(`压缩后大小: ${(totalCompressedSize / 1024 / 1024).toFixed(2)}MB`);
    console.log(`压缩率: ${((1 - totalCompressedSize / totalOriginalSize) * 100).toFixed(1)}%`);
  }
}

// 删除未使用的 worker 文件
function deleteUnusedWorkers() {
  const jsDir = path.join(__dirname, '../nfd-front/js');
  const workers = ['editor.worker.js', 'editor.worker.js.gz', 'json.worker.js', 'json.worker.js.gz', 'ts.worker.js', 'ts.worker.js.gz'];
  
  let deletedCount = 0;
  for (const worker of workers) {
    const filePath = path.join(jsDir, worker);
    if (fs.existsSync(filePath)) {
      try {
        fs.unlinkSync(filePath);
        deletedCount++;
        console.log(`✓ 已删除未使用的文件: ${worker}`);
      } catch (error) {
        console.warn(`⚠ 删除失败: ${worker}`, error.message);
      }
    }
  }
  
  if (deletedCount > 0) {
    console.log(`\n已删除 ${deletedCount} 个未使用的 worker 文件\n`);
  }
}

// 复制到 webroot
function copyToWebroot() {
  const source = path.join(__dirname, '../nfd-front');
  const dest = path.join(__dirname, '../../webroot/nfd-front');
  
  // 使用 FileManagerPlugin 的方式，这里用简单的复制
  const { execSync } = require('child_process');
  try {
    // 删除目标目录
    if (fs.existsSync(dest)) {
      execSync(`rm -rf "${dest}"`, { stdio: 'inherit' });
    }
    // 复制整个目录
    execSync(`cp -R "${source}" "${dest}"`, { stdio: 'inherit' });
    console.log('\n✓ 已复制到 webroot');
  } catch (error) {
    console.error('\n✗ 复制到 webroot 失败:', error.message);
    process.exit(1);
  }
}

// 主函数
async function main() {
  // 先删除未使用的 worker 文件
  deleteUnusedWorkers();
  
  // 然后压缩 vs 目录
  const vsPath = path.join(__dirname, '../nfd-front/js/vs');
  console.log('开始压缩 vs 目录下的文件...\n');
  try {
    await compressDirectory(vsPath, 1024); // 只压缩超过1KB的文件
    console.log('\n✓ vs 目录压缩完成');
  } catch (error) {
    console.error('\n✗ vs 目录压缩失败:', error);
    process.exit(1);
  }
  
  // 最后复制到 webroot
  copyToWebroot();
}

main();

