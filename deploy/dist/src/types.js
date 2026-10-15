export class GameError extends Error {
}
export const choice = (label, message = label) => ({ label, message });
export const shareChoice = (label = '📤 공유하기') => ({ label, message: '', kind: 'share' });
