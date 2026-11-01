import sharp from 'sharp';
import {createHash} from 'node:crypto';

export type ImageFormat = 'png' | 'jpeg' | 'webp';
export const MIME: Record<ImageFormat, string> = {png: 'image/png', jpeg: 'image/jpeg', webp: 'image/webp'};

export type ImageLimits = {
  maxBytes: number;
  maxWidth: number;
  maxHeight: number;
  maxPixels: number;
};

export const DEFAULT_IMAGE_LIMITS: ImageLimits = Object.freeze({
  maxBytes: 8 * 1024 * 1024,
  maxWidth: 6000,
  maxHeight: 6000,
  maxPixels: 24_000_000,
});

export class ImageRejected extends Error {
  constructor(readonly code: 'EMPTY' | 'TOO_LARGE' | 'UNSUPPORTED_TYPE' | 'SIGNATURE_MISMATCH' | 'DECODE_FAILED' | 'DIMENSIONS' | 'ANIMATED') {
    super(code);
  }
}

export type CleanImage = {
  format: ImageFormat;
  mime: string;
  width: number;
  height: number;
  data: Buffer;
  sha256: string;
};

/** 파일 앞부분 서명으로 실제 형식을 판정한다. 확장자·Content-Type 은 보지 않는다. */
export function sniffFormat(data: Buffer): ImageFormat | null {
  if (data.length >= 8 && data.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'png';
  if (data.length >= 3 && data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff) return 'jpeg';
  if (data.length >= 12 && data.toString('latin1', 0, 4) === 'RIFF' && data.toString('latin1', 8, 12) === 'WEBP') return 'webp';
  return null;
}

/**
 * 업로드 이미지를 검사하고 다시 인코딩한다.
 * - 서명(PNG/JPEG/WebP)만 허용. SVG·HTML·GIF·기타는 거부
 * - 실제 디코딩 성공, 가로·세로·픽셀 수·용량 제한
 * - 애니메이션(여러 프레임) 거부
 * - EXIF/ICC/XMP 등 메타데이터를 남기지 않고 방향만 픽셀에 반영해 다시 저장
 */
export async function cleanImage(input: Buffer, limits: ImageLimits = DEFAULT_IMAGE_LIMITS): Promise<CleanImage> {
  if (!Buffer.isBuffer(input) || input.length === 0) throw new ImageRejected('EMPTY');
  if (input.length > limits.maxBytes) throw new ImageRejected('TOO_LARGE');
  const format = sniffFormat(input);
  if (!format) throw new ImageRejected('UNSUPPORTED_TYPE');
  let meta: Awaited<ReturnType<ReturnType<typeof sharp>['metadata']>>;
  try {
    meta = await sharp(input, {limitInputPixels: limits.maxPixels, failOn: 'error'}).metadata();
  } catch (error) {
    // 선언된 크기가 픽셀 한도를 넘으면 libvips 가 디코딩 전에 멈춘다(픽셀 폭탄 방지).
    throw new ImageRejected(/pixel limit/i.test(String((error as Error)?.message)) ? 'DIMENSIONS' : 'DECODE_FAILED');
  }
  const decoded = String(meta.format) === 'jpg' ? 'jpeg' : String(meta.format);
  if (decoded !== format) throw new ImageRejected('SIGNATURE_MISMATCH');
  if ((meta.pages ?? 1) > 1) throw new ImageRejected('ANIMATED');
  const width = meta.width ?? 0, height = meta.height ?? 0;
  if (width < 1 || height < 1 || width > limits.maxWidth || height > limits.maxHeight || width * height > limits.maxPixels) throw new ImageRejected('DIMENSIONS');
  let output: {data: Buffer; info: {width: number; height: number}};
  try {
    // 전체를 실제로 디코딩한다(잘린·손상된 파일은 여기서 실패). withMetadata 를 쓰지 않으므로 메타데이터는 버려진다.
    let pipeline = sharp(input, {limitInputPixels: limits.maxPixels, failOn: 'error'}).rotate();
    pipeline = format === 'png' ? pipeline.png({compressionLevel: 9}) : format === 'jpeg' ? pipeline.jpeg({quality: 90, mozjpeg: true}) : pipeline.webp({quality: 90});
    output = await pipeline.toBuffer({resolveWithObject: true});
  } catch {
    throw new ImageRejected('DECODE_FAILED');
  }
  if (output.data.length > limits.maxBytes) throw new ImageRejected('TOO_LARGE');
  return {
    format,
    mime: MIME[format],
    width: output.info.width,
    height: output.info.height,
    data: output.data,
    sha256: createHash('sha256').update(output.data).digest('hex'),
  };
}
