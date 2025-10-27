

type Props = {
  url?: string | null;
  mediaType?: string | null;
  className?: string;
};

function guessMimeFromUrl(url?: string | null): string | null {
  if (!url) return null;
  const q = url.split("?")[0].toLowerCase();
  if (q.endsWith(".jpg") || q.endsWith(".jpeg")) return "image/jpeg";
  if (q.endsWith(".png")) return "image/png";
  if (q.endsWith(".webp")) return "image/webp";
  if (q.endsWith(".gif")) return "image/gif";
  if (q.endsWith(".mp4")) return "video/mp4";
  if (q.endsWith(".mov") || q.endsWith(".qt")) return "video/quicktime";
  if (q.endsWith(".webm")) return "video/webm";
  return null;
}

export default function MediaProof({ url, mediaType, className }: Props) {
  if (!url) return <p className="text-sm text-gray-500">No proof.</p>;

  const mt = (mediaType || guessMimeFromUrl(url) || "").toLowerCase();
  const isImg = mt.startsWith("image/");
  const isVid = mt.startsWith("video/");

  if (isImg) {
    return (
      <a href={url} target="_blank" rel="noreferrer" title="Open original">
        <img
          src={url}
          alt="proof"
          className={className || "max-w-full h-auto rounded-lg border"}
          loading="lazy"
          referrerPolicy="no-referrer"
        />
      </a>
    );
  }

  if (isVid) {
    return (
      <video
        className={className || "max-w-full rounded-lg border"}
        src={url}
        controls
        playsInline
        preload="metadata"
      />
    );
  }

  // Fallback: unknown file type -> link
  return (
    <a className="link" href={url} target="_blank" rel="noreferrer">
      Open proof
    </a>
  );
}
