/**
 * 공지·이미지 쓰기 작업을 한 줄로 세운다(같은 프로세스 안).
 * 중복 요청 판정, 버전 검사, 참조 검사가 서로 끼어들지 않게 한다.
 * 여러 서버 프로세스를 띄우는 구성에서는 저장소 트랜잭션·고유 제약으로 대체해야 한다.
 */
export class SerialQueue {
  private tail: Promise<unknown> = Promise.resolve();
  run<T>(task: () => Promise<T>): Promise<T> {
    const next = this.tail.then(task, task);
    this.tail = next.catch(() => undefined);
    return next;
  }
}
