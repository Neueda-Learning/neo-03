// Havn glass panel — React. Requires glass.css imported at app root.
// <GlassPanel>frost</GlassPanel>
// <GlassPanel accent="#D6337E" as="header">team-tinted (max one per desk)</GlassPanel>
export const TEAM_ACCENTS = {
  cards: '#A6C918', lending: '#E8A020', deposits: '#17A67E', fraud: '#D6337E', kyc: '#2E86C9',
  disputes: '#D9482B', payments: '#12A3B4', treasury: '#4353C4', collections: '#4E9E3A', support: '#8A4FD0',
};

export default function GlassPanel({ as: Tag = 'div', accent, team, className = '', style, children, ...rest }) {
  const tint = accent ?? (team ? TEAM_ACCENTS[team] : undefined);
  return (
    <Tag
      className={`havn-glass${tint ? ' havn-glass--team' : ''}${className ? ' ' + className : ''}`}
      style={tint ? { '--team-accent': tint, ...style } : style}
      {...rest}
    >
      {children}
    </Tag>
  );
}
