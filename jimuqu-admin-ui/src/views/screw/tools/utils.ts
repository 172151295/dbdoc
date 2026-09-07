/**
 * 常用工具共享工具函数（全部纯前端，无后端依赖）
 */
import { downloadByData } from '@/utils/file/download';

/** UTF-8 安全 Base64 编码 */
export function utf8ToBase64(s: string): string {
  const bytes = new TextEncoder().encode(s);
  let bin = '';
  for (const b of bytes) {
    bin += String.fromCharCode(b);
  }
  return btoa(bin);
}

/** UTF-8 安全 Base64 解码 */
export function base64ToUtf8(b: string): string {
  const bin = atob(b.replace(/\s+/g, ''));
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** 字符串 → Hex（UTF-8 字节） */
export function strToHex(s: string): string {
  const bytes = new TextEncoder().encode(s);
  return Array.from(bytes, (x) => x.toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase();
}

/** Hex → 字符串（UTF-8 字节） */
export function hexToStr(h: string): string {
  const clean = h.replace(/\s+/g, '');
  if (clean.length % 2 !== 0) {
    throw new Error('Hex 长度必须为偶数');
  }
  const bytes = new Uint8Array(clean.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return new TextDecoder().decode(bytes);
}

/** 中文 → \uXXXX 转义（ASCII 保留） */
export function toUnicodeEsc(s: string): string {
  return Array.from(s)
    .map((c) =>
      c.charCodeAt(0) > 127
        ? `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`
        : c,
    )
    .join('');
}

/** \uXXXX → 中文 */
export function fromUnicodeEsc(s: string): string {
  return s.replace(/\\u([0-9a-fA-F]{4})/g, (_m, h: string) =>
    String.fromCharCode(Number.parseInt(h, 16)),
  );
}

export function urlEncode(s: string, full: boolean): string {
  return full ? encodeURI(s) : encodeURIComponent(s);
}

export function urlDecode(s: string, full: boolean): string {
  return full ? decodeURI(s) : decodeURIComponent(s);
}

/** 复制文本（带降级） */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      return true;
    } catch {
      return false;
    }
  }
}

/** 下载文本文件（自动加 UTF-8 BOM，兼容 Excel/记事本） */
export function downloadText(
  filename: string,
  text: string,
  mime = 'text/plain',
): void {
  const bom = '\uFEFF';
  downloadByData(
    new Blob([bom + text], { type: `${mime};charset=utf-8` }),
    filename,
    mime,
  );
}

/** JSON 校验并格式化（缩进可选） */
export function formatJson(text: string, indent = 2): string {
  return JSON.stringify(JSON.parse(text), null, indent);
}

/** 下载 base64 图片（dataURL） */
export function downloadDataUrl(dataUrl: string, filename: string): void {
  downloadByData(dataUrl, filename);
}

/** 驼峰化：user_name → userName */
export function toCamel(s: string): string {
  return s
    .replace(/[_\-\s]+(.)/g, (_m, c: string) => c.toUpperCase())
    .replace(/^[A-Z]/, (c) => c.toLowerCase());
}

/** 帕斯卡化：user_name → UserName */
export function toPascal(s: string): string {
  const c = toCamel(s);
  return c ? c.charAt(0).toUpperCase() + c.slice(1) : c;
}

/** 判断字符串是否为合法 JSON */
export function isJson(text: string): boolean {
  try {
    JSON.parse(text);
    return true;
  } catch {
    return false;
  }
}

/** 数字 → 人民币大写 */
export function rmbUpper(n: number): string {
  if (!Number.isFinite(n)) {
    throw new Error('请输入有效数字');
  }
  const DIGITS = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'];
  const UNITS = ['', '拾', '佰', '仟'];
  const GROUPS = ['', '万', '亿', '兆'];

  const neg = n < 0;
  n = Math.abs(n);
  const yuan = Math.floor(n);
  const cents = Math.round((n - yuan) * 100);
  const jiao = Math.floor(cents / 10);
  const fen = cents % 10;

  const groupToStr = (v: number): string => {
    let s = '';
    let zero = false;
    let unitPos = 0;
    while (v > 0) {
      const d = v % 10;
      if (d === 0) {
        if (s && !zero) {
          s = '零' + s;
        }
        zero = true;
      } else {
        s = (DIGITS[d] ?? '') + (UNITS[unitPos] ?? '') + s;
        zero = false;
      }
      unitPos++;
      v = Math.floor(v / 10);
    }
    return s;
  };

  let intPart = '';
  if (yuan === 0) {
    intPart = '零';
  } else {
    let v = yuan;
    let g = 0;
    while (v > 0) {
      const seg = v % 10000;
      const segStr = groupToStr(seg);
      const groupUnit = GROUPS[g];
      if (segStr) {
        intPart = segStr + groupUnit + intPart;
      } else if (intPart && !intPart.startsWith('零')) {
        intPart = '零' + intPart;
      }
      v = Math.floor(v / 10000);
      g++;
    }
  }

  let result = (neg ? '负' : '') + intPart + '元';
  if (jiao === 0 && fen === 0) {
    result += '整';
  } else {
    if (jiao > 0) {
      result += DIGITS[jiao] + '角';
    } else {
      result += '零';
    }
    if (fen > 0) {
      result += DIGITS[fen] + '分';
    }
  }
  return result;
}
