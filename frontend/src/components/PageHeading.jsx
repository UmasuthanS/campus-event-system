export default function PageHeading({ eyebrow, title, children }) {
  return <div className="page-heading"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="intro">{children}</p></div>;
}
